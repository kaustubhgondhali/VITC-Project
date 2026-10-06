package com.vitc.service;

import com.vitc.dto.request.ContactMessageRequest;
import com.vitc.dto.response.ContactMessageResponse;
import java.util.List;

public interface ContactMessageService {

    List<ContactMessageResponse> getAll();

    ContactMessageResponse getById(Long id);

    ContactMessageResponse create(ContactMessageRequest request);

    ContactMessageResponse update(Long id, ContactMessageRequest request);

    void delete(Long id);

    List<ContactMessageResponse> getUnhandled();

    ContactMessageResponse markHandled(Long id);
}
