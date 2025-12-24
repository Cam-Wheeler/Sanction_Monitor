package com.camwheeler.transactiongenerator.model;

import java.util.UUID;

public record TransactionParty(
    String partyType,
    UUID UID, 
    String name,
    String nationality,
    UUID accountNumber, 
    String bank,  
    String location
) {}