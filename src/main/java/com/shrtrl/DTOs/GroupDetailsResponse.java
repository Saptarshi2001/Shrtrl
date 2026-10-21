package com.shrtrl.DTOs;

import java.util.List;

public record GroupDetailsResponse(
        int groupId,
        String groupName,
        List<GroupUrlResponse> urls
) {}
