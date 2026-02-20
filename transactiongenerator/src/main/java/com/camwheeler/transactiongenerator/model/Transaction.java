package com.camwheeler.transactiongenerator.model;

import java.util.UUID;

public record Transaction(
    UUID transactionId,
    double amount,
    String date,
    String time,
    String type, 
    TransactionParty sender,
    TransactionParty receiver
) {}