package org.example.dto;

import lombok.Data;

@Data
public class RegisterRequestDTO {
    private String username;   //用户名 不可重复
    private String password;
    private String name;  //游戏昵称 可重复
}
