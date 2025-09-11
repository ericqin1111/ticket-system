package org.example.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {
    Long id;
    String username;
    String passwordHash;
    String phoneNumber;
    LocalDateTime createTime;
    LocalDateTime updateTime;
}
