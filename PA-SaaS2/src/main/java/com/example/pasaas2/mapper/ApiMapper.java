package com.example.pasaas2.mapper;

import com.example.pasaas2.dto.InviteCodeResponse;
import com.example.pasaas2.dto.MemberResponse;
import com.example.pasaas2.dto.MembershipInfo;
import com.example.pasaas2.dto.OrganizationResponse;
import com.example.pasaas2.model.InviteCode;
import com.example.pasaas2.model.Membership;
import com.example.pasaas2.model.Organization;

/** Conversiones entidad -> DTO. Deben llamarse dentro de una transacción (hay relaciones LAZY). */
public final class ApiMapper {

    private ApiMapper() {
    }

    public static OrganizationResponse toOrganizationResponse(Organization org) {
        return OrganizationResponse.builder()
                .id(org.getId())
                .name(org.getName())
                .plan(org.getPlan())
                .planEndDate(org.getPlanEndDate())
                .createdAt(org.getCreatedAt())
                .build();
    }

    public static MembershipInfo toMembershipInfo(Membership m) {
        Organization org = m.getOrganization();
        return MembershipInfo.builder()
                .organizationId(org.getId())
                .organizationName(org.getName())
                .role(m.getRole())
                .joinedAt(m.getJoinedAt())
                .plan(org.getPlan())
                .planEndDate(org.getPlanEndDate())
                .build();
    }

    public static MemberResponse toMemberResponse(Membership m) {
        return MemberResponse.builder()
                .userId(m.getUser().getId())
                .name(m.getUser().getName())
                .email(m.getUser().getEmail())
                .role(m.getRole())
                .joinedAt(m.getJoinedAt())
                .build();
    }

    public static InviteCodeResponse toInviteCodeResponse(InviteCode code) {
        return InviteCodeResponse.builder()
                .id(code.getId())
                .code(code.getCode())
                .maxUses(code.getMaxUses())
                .usesCount(code.getUsesCount())
                .expiresAt(code.getExpiresAt())
                .createdAt(code.getCreatedAt())
                .build();
    }
}
