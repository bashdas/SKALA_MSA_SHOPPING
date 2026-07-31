package com.skala.userservice.customer.domain;

import com.skala.userservice.common.entity.BaseTimeEntity;
import com.skala.userservice.customer.exception.CustomerAlreadyWithdrawnException;
import com.skala.userservice.customer.exception.InsufficientPointException;
import com.skala.userservice.customer.exception.InvalidPointAmountException;
import com.skala.userservice.customer.exception.WithdrawnCustomerException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "customers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer extends BaseTimeEntity {

    public static final long INITIAL_POINT = 10_000L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 50)
    @Column(name = "login_id", nullable = false, unique = true, length = 50)
    private String loginId;

    @NotBlank
    @Column(nullable = false)
    private String password;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String name;

    @PositiveOrZero
    @Column(nullable = false)
    private long point;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStatus status;

    private Customer(String loginId, String password, String name) {
        this.loginId = requireText(loginId, "loginId");
        this.password = requireText(password, "password");
        this.name = requireText(name, "name");
        requireMaxLength(this.loginId, 50, "loginId");
        requireMaxLength(this.name, 50, "name");
        this.point = INITIAL_POINT;
        this.status = CustomerStatus.ACTIVE;
    }

    public static Customer create(String loginId, String password, String name) {
        return new Customer(loginId, password, name);
    }

    public void changeName(String name) {
        if (status == CustomerStatus.WITHDRAWN) {
            throw new WithdrawnCustomerException();
        }
        String validatedName = requireText(name, "name");
        requireMaxLength(validatedName, 50, "name");
        this.name = validatedName;
    }

    public void withdraw() {
        if (status == CustomerStatus.WITHDRAWN) {
            throw new CustomerAlreadyWithdrawnException();
        }
        status = CustomerStatus.WITHDRAWN;
    }

    public void deductPoint(long amount) {
        validatePositiveAmount(amount);
        if (point < amount) {
            throw new InsufficientPointException(point, amount);
        }
        point -= amount;
    }

    public void refundPoint(long amount) {
        validatePositiveAmount(amount);
        point = Math.addExact(point, amount);
    }

    private static void validatePositiveAmount(long amount) {
        if (amount <= 0) {
            throw new InvalidPointAmountException(amount);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static void requireMaxLength(String value, int maxLength, String fieldName) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
        }
    }
}
