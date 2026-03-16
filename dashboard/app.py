import os

import pandas as pd
import psycopg2
import streamlit as st

DATABASE_URL = os.getenv(
    "DATABASE_URL",
    "postgresql://dashboard_user:dashboard_pass@dashboard-db:5432/dashboard",
)

st.set_page_config(
    page_title="Sanction Monitor",
    page_icon="🛡️",
    layout="wide",
    initial_sidebar_state="collapsed",
)

st.markdown(
    """
    <style>
        .flagged-header { color: #ff4b4b; }
        .approved-header { color: #21c55d; }
        .metric-card { background: #1e1e2e; border-radius: 8px; padding: 12px; }
    </style>
    """,
    unsafe_allow_html=True,
)


def get_connection():
    return psycopg2.connect(DATABASE_URL)


@st.cache_data(ttl=15)
def load_stats() -> pd.Series:
    with get_connection() as conn:
        df = pd.read_sql_query(
            """
            SELECT
                COUNT(*)                                              AS total,
                SUM(CASE WHEN flagged = true  THEN 1 ELSE 0 END)    AS flagged,
                SUM(CASE WHEN flagged = false THEN 1 ELSE 0 END)    AS approved
            FROM transactions
            """,
            conn,
        )
    return df.iloc[0]


@st.cache_data(ttl=15)
def load_flagged() -> pd.DataFrame:
    with get_connection() as conn:
        return pd.read_sql_query(
            """
            SELECT
                transaction_id,
                amount, date, time, type,
                sender_name, sender_nationality, sender_bank, sender_location,
                receiver_name, receiver_nationality, receiver_bank, receiver_location,
                sender_match_score,   sender_match_name,
                receiver_match_score, receiver_match_name,
                verdict, confidence, reasoning, model, analysed_at, status
            FROM transactions
            WHERE flagged = true
            ORDER BY ingested_at DESC
            """,
            conn,
        )


@st.cache_data(ttl=15)
def load_approved() -> pd.DataFrame:
    with get_connection() as conn:
        return pd.read_sql_query(
            """
            SELECT
                transaction_id,
                amount, date, time, type,
                sender_name, sender_bank, sender_location,
                receiver_name, receiver_bank, receiver_location
            FROM transactions
            WHERE flagged = false
            ORDER BY ingested_at DESC
            LIMIT 200
            """,
            conn,
        )


# ─── Header ──────────────────────────────────────────────────────────────────

col_title, col_refresh = st.columns([6, 1])
with col_title:
    st.markdown("# 🛡️ Sanction Monitor")
    st.caption("Real-time transaction screening against the UK Sanctions List")
with col_refresh:
    st.write("")
    if st.button("↻ Refresh", use_container_width=True):
        st.cache_data.clear()
        st.rerun()

st.divider()

# ─── Stats ────────────────────────────────────────────────────────────────────

try:
    stats = load_stats()
except Exception as e:
    st.error(f"Cannot connect to dashboard database: {e}")
    st.stop()

total    = int(stats["total"])
flagged  = int(stats["flagged"])
approved = int(stats["approved"])
flag_rate = f"{flagged / total * 100:.1f}%" if total > 0 else "—"

c1, c2, c3, c4 = st.columns(4)
c1.metric("Total Transactions", f"{total:,}")
c2.metric("🔴 Flagged",         f"{flagged:,}")
c3.metric("✅ Approved",         f"{approved:,}")
c4.metric("Flag Rate",           flag_rate)

st.divider()

# ─── Flagged Transactions ─────────────────────────────────────────────────────

st.markdown("## 🔴 Flagged Transactions")

flagged_df = load_flagged()

if flagged_df.empty:
    st.info("No flagged transactions yet — the pipeline may still be warming up.")
else:
    pending = flagged_df[flagged_df["verdict"].isna()]
    analysed = flagged_df[flagged_df["verdict"].notna()]

    if not pending.empty:
        st.warning(f"{len(pending)} transaction(s) flagged but awaiting AI analysis.")

    st.markdown(f"**{len(analysed)} transaction(s) reviewed by AI**")

    VERDICT_ICON  = {"CONFIRMED": "🔴", "POSSIBLE": "🟡", "CLEARED": "🟢"}
    VERDICT_COLOR = {"CONFIRMED": "red", "POSSIBLE": "orange", "CLEARED": "green"}

    for _, row in flagged_df.iterrows():
        sender_score   = row["sender_match_score"]   or 0.0
        receiver_score = row["receiver_match_score"] or 0.0

        # Primary matched party is the higher-scoring one
        if sender_score >= receiver_score and sender_score > 0:
            primary_party   = "Sender"
            primary_name    = row["sender_name"]
            primary_sanction = row["sender_match_name"] or "—"
            primary_score   = sender_score
        else:
            primary_party   = "Receiver"
            primary_name    = row["receiver_name"]
            primary_sanction = row["receiver_match_name"] or "—"
            primary_score   = receiver_score

        verdict     = row["verdict"] or "PENDING"
        icon        = VERDICT_ICON.get(verdict, "⏳")
        confidence  = f"{round(row['confidence'] * 100)}%" if row["confidence"] is not None else "—"
        amount      = f"£{row['amount']:,.2f}"

        label = (
            f"{icon} **{primary_name}** → matched against **{primary_sanction}** "
            f"(score: {primary_score:.2f})  |  Verdict: **{verdict}** ({confidence})  |  "
            f"{amount} on {row['date']}"
        )

        with st.expander(label):
            left, right = st.columns(2)

            with left:
                st.markdown("#### Transaction")
                st.markdown(
                    f"- **ID:** `{str(row['transaction_id'])[:8]}…`\n"
                    f"- **Amount:** {amount}\n"
                    f"- **Date/Time:** {row['date']} {row['time']}\n"
                    f"- **Type:** {row['type']}\n"
                    f"- **Status:** {row['status']}"
                )

                st.markdown("#### Sender")
                st.markdown(
                    f"- **Name:** {row['sender_name']}\n"
                    f"- **Nationality:** {row['sender_nationality']}\n"
                    f"- **Bank:** {row['sender_bank']}\n"
                    f"- **Location:** {row['sender_location']}"
                )

                st.markdown("#### Receiver")
                st.markdown(
                    f"- **Name:** {row['receiver_name']}\n"
                    f"- **Nationality:** {row['receiver_nationality']}\n"
                    f"- **Bank:** {row['receiver_bank']}\n"
                    f"- **Location:** {row['receiver_location']}"
                )

            with right:
                st.markdown("#### Sanction Match")
                rows = []
                if sender_score > 0:
                    rows.append(
                        f"- **Sender** `{row['sender_name']}` → "
                        f"`{row['sender_match_name']}` (score: {sender_score:.4f})"
                    )
                if receiver_score > 0:
                    rows.append(
                        f"- **Receiver** `{row['receiver_name']}` → "
                        f"`{row['receiver_match_name']}` (score: {receiver_score:.4f})"
                    )
                st.markdown("\n".join(rows) if rows else "_No match data_")

                st.markdown("#### AI Verdict")
                color = VERDICT_COLOR.get(verdict, "gray")
                st.markdown(
                    f"- **Verdict:** :{color}[**{verdict}**]\n"
                    f"- **Confidence:** {confidence}\n"
                    f"- **Model:** {row['model'] or '—'}\n"
                    f"- **Analysed:** {row['analysed_at'] or '—'}"
                )

                st.markdown("#### Model Reasoning")
                if row["reasoning"]:
                    st.info(row["reasoning"])
                else:
                    st.warning("Awaiting analysis…")

st.divider()

# ─── Approved Transactions ────────────────────────────────────────────────────

st.markdown("## ✅ Approved Transactions")

approved_df = load_approved()

if approved_df.empty:
    st.info("No approved transactions yet.")
else:
    st.caption(f"Showing latest {len(approved_df)} approved transactions")

    display = approved_df.copy()
    display["transaction_id"] = display["transaction_id"].astype(str).str[:8] + "…"
    display["amount"] = display["amount"].map("£{:,.2f}".format)
    display = display.rename(columns={
        "transaction_id": "ID",
        "amount":         "Amount",
        "date":           "Date",
        "time":           "Time",
        "type":           "Type",
        "sender_name":    "Sender",
        "sender_bank":    "Sender Bank",
        "sender_location":"From",
        "receiver_name":  "Receiver",
        "receiver_bank":  "Receiver Bank",
        "receiver_location": "To",
    })

    st.dataframe(display, use_container_width=True, hide_index=True)
