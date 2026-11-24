package com.ravita.offer.api.dto;

import java.util.List;

/** Page<T> do contrato: page começa em 1; total conta todos os resultados do filtro. */
public record PageResponse<T>(List<T> items, int page, int size, long total) {
}
