"""
Code to process the sanction doc into a database.
CSV file downloaded from: https://www.gov.uk/government/publications/the-uk-sanctions-list
"""

import json
from pathlib import Path
from time import perf_counter
import pandas as pd

def main() -> None:
    """
    Process the csv dataset from UK Sanctions into a more sterile version for our
    application. 
    """
    csv_path = Path("./data/UK-Sanctions-List.csv")
    data_frame = pd.read_csv(csv_path, header=1)
    print(f"Columns: {data_frame.columns}")

    # Lets start with individuals (we can work on the entities later)
    individuals = data_frame[data_frame["Designation Type"] == "Individual"]
    print(f"We have gone from {len(data_frame)} rows to {len(individuals)} rows when looking at individuals")

    # Refine again to individuals sanctioned by the UK and the UK alongside the UN.
    uk_sanction_idv = individuals[individuals["Designation source"].isin(["UK", "UK|UN"])]
    print(f"We have got {len(uk_sanction_idv)} number of rows who are sanctioned by the UK or the UK and UN")

    # Remove the aliases (for now)
    primary_names = uk_sanction_idv[uk_sanction_idv["Name type"] == "Primary name"]
    print(f"We have got {len(primary_names)} number of primary names.")

    # Fill in n/a's
    primary_names = primary_names.fillna({
        "Gender": "Unknown",
        "Nationality(/ies)": "Unknown",
        "D.O.B": "Unknown",
        "Position": "Unknown",
        "Other Information": ""
    })

    # Now that we have the individuals what is most useful to us right now?
    db = {}
    dupes = [] # We will just use name for duplicates for now
    id = 0
    for _, row in primary_names.iterrows():
        complete_name = " ".join(str(name) for name in [row["Name 1"], row["Name 2"], row["Name 3"], row["Name 4"], row["Name 5"], row["Name 1"]] if str(name) != "nan")
        if complete_name in dupes:
            continue
        nationality = row["Nationality(/ies)"]
        gender = row["Gender"]
        dob = row["D.O.B"]
        position = row["Position"]
        sanctions = row["Sanctions Imposed"]
        sanction_creator = row["Designation source"]
        reason = row["UK Statement of Reasons"]
        other_information = row["Other Information"]

        key = id
        individual = {
            "name": complete_name,
            "nationality": nationality,
            "gender": gender,
            "D.O.B": dob,
            "position": position,
            "sanctions": sanctions,
            "sanction creator": sanction_creator,
            "reason": reason,
            "other information": other_information
        } 
        
        db[key] = individual
        dupes.append(complete_name)
        id += 1

    # Now we have our cleaned set of people.
    sanction_file_path = Path("./data/Cleaned-Sanctions.json")
    with open(sanction_file_path, "w") as f:
        json.dump(db, f, indent=4)
    
if __name__ == "__main__": 
    print("Starting dataset curation.")
    start = perf_counter()
    main()
    stop = perf_counter()
    print(f"Finished dataset curation in {stop - start} seconds")
