package com.allpick.new_allpick.user.domain.entity;

import com.allpick.new_allpick.common.domain.model.BaseEntity;
import org.hibernate.annotations.DynamicInsert;
import jakarta.persistence.*;
import lombok.*;

/**
 * [포트폴리오 발췌] 사용자-역할 부여 (다대다).
 * 한 사용자가 여러 역할을 동시에 보유할 수 있도록 역할을 별도 테이블로 분리하고,
 * (uid, role_type) 유니크 제약으로 동일 역할 중복 부여를 DB 레벨에서 차단한다.
 */
@Entity
@DynamicInsert
@Table(
		name = "ap_user_roles",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_ap_user_roles_uid_role", columnNames = {"uid", "role_type"})
		},
		indexes = {
				@Index(name = "idx_ap_user_roles_uid", columnList = "uid"),
				@Index(name = "idx_ap_user_roles_role_type", columnList = "role_type")
		}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserRoleGrant extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "grant_id")
	private Long grantId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "uid", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(name = "role_type", nullable = false, length = 30)
	@Builder.Default
	private UserRole roleType = UserRole.USER;

	public static UserRoleGrant of(User user, UserRole roleType) {
		return UserRoleGrant.builder().user(user).roleType(roleType).build();
	}
}
