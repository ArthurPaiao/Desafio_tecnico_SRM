package com.arthurpaiao.creditengine.application;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

final class PageRequests {
    private PageRequests() {}

    static PageRequest page(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Página deve ser não negativa, tamanho entre 1 e 100 e deslocamento até 2147483647");
        }
        return PageRequest.of(page, size, sort);
    }
}
