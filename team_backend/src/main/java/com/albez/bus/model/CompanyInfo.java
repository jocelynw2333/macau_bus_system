package com.albez.bus.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("company_info")
public class CompanyInfo {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String color;

    @TableField(exist = false)
    private String name;

    private String nameZhCn;
    private String nameZhTw;
    private String nameEn;
    private String namePt;
}
