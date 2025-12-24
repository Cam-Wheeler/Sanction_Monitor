package com.camwheeler.transactiongenerator.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record Transaction(
    UUID transactionId,
    double amount,
    LocalDate date,
    LocalTime time,
    String type, 
    TransactionParty sender,
    TransactionParty receiver
) {}