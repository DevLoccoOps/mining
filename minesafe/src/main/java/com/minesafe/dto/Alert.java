package com.minesafe.dto;

/** A single safety alert emitted by the safety engine. */
public record Alert(String type, String message) {}