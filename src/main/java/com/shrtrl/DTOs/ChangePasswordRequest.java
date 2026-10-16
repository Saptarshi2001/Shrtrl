package com.shrtrl.DTOs;

public record ChangePasswordRequest(String oldPassword, String newPassword) {
}