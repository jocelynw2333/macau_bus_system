package com.albez.bus.model;

import lombok.Data;

/** A station in snapshot route order. */
@Data
public class SnapshotStation {
    private String code;
    private String name;
    private Integer index;
}
