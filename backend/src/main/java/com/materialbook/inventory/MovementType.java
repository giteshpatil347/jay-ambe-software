package com.materialbook.inventory;

/** Mirrors the stock_movements.movement_type CHECK. Sign of quantity_delta: OPENING/IN/REVERSAL_IN positive, OUT/REVERSAL_OUT negative. */
public enum MovementType { OPENING, IN, OUT, REVERSAL_IN, REVERSAL_OUT }
