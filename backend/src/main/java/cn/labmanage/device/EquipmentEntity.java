package cn.labmanage.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;

@TableName("equipment")
public class EquipmentEntity {
    @TableId(type = IdType.AUTO) public Long id;
    public String code;
    public String name;
    public Long categoryId;
    public Long labId;
    public String brandModel;
    public String location;
    public Long ownerId;
    public String description;
    public String usageRules;
    public String imageUrl;
    public LocalDate purchaseDate;
    public String status;
}
