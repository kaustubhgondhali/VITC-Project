package com.vitc.service;

import com.vitc.dto.request.GalleryCategoryRequest;
import java.util.List;

public interface GalleryCategoryService {

    List<String> getAll();

    String create(GalleryCategoryRequest request);
}