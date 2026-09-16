package com.arthurpaiao.creditengine.persistence;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "assignors")
public class Assignor {
    @Id private UUID id;
    @Column(nullable = false, length = 40) private String code;
    @Column(nullable = false, length = 200) private String name;

    protected Assignor() {}
    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
}
