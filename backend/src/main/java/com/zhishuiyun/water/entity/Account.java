package com.zhishuiyun.water.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录名，唯一 */
    @Column(unique = true, nullable = false)
    private String username;

    /** BCrypt 哈希，禁止明文 */
    @Column(nullable = false)
    @JsonIgnore
    private String password;

    /** 姓名 / 名称 */
    private String name;

    /** 联系电话，仅本人和管理员可见。 */
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DomainEnums.AccountRole role;

    /** 运营人员限定的区域；运营人员为空时无辖区权限。 */
    private String areaScope;

    /** 普通用户绑定的水表编号 */
    private String boundMeterId;

    @Builder.Default
    private Boolean enabled = Boolean.TRUE;
}
