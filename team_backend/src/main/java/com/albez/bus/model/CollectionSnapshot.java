package com.albez.bus.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("collection_snapshot")
public class CollectionSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDateTime collectedAt;
    private String routeName;
    private Integer direction;
    private Integer stationCount;
    private Integer busCount;
}
