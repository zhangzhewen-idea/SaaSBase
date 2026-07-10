package com.saasbase.common.api;

import java.util.List;

public record PageResponse<T>(List<T> items, long total, long pageNo, long pageSize) {
}
