package com.sms.backend.entity;

import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.enums.RoleName;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "role", schema = "dbo")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Long roleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_name", nullable = false, length = 50)
    private RoleName roleName;

    @Nationalized
    @Column(name = "permissions", columnDefinition = "nvarchar(max)")
    private String permissions;

    @Nationalized
    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GeneralStatus status = GeneralStatus.ACTIVE;
}
