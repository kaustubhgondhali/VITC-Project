package com.vitc.service.impl;

import com.vitc.dto.request.ContactMessageRequest;
import com.vitc.dto.response.ContactMessageResponse;
import com.vitc.entity.ContactMessage;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.mapper.ContactMessageMapper;
import com.vitc.repository.ContactMessageRepository;
import com.vitc.service.ContactMessageService;
import com.vitc.service.OwnerNotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContactMessageServiceImpl implements ContactMessageService {

    private final ContactMessageRepository repository;
    private final OwnerNotificationService ownerNotificationService;

    @Override
    public List<ContactMessageResponse> getAll() {
        return repository.findAll().stream().map(ContactMessageMapper::toResponse).toList();
    }

    @Override
    public ContactMessageResponse getById(Long id) {
        return ContactMessageMapper.toResponse(find(id));
    }

    @Override
    @Transactional
    public ContactMessageResponse create(ContactMessageRequest request) {
        ContactMessage entity = ContactMessageMapper.toEntity(request);
        ContactMessage saved = repository.save(entity);
        ownerNotificationService.contactMessageReceived(saved);
        return ContactMessageMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ContactMessageResponse update(Long id, ContactMessageRequest request) {
        ContactMessage entity = find(id);
        ContactMessageMapper.apply(entity, request);
        return ContactMessageMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    @Override
    public List<ContactMessageResponse> getUnhandled() {
        return repository.findByHandledFalseOrderByCreatedAtDesc().stream()
                .map(ContactMessageMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public ContactMessageResponse markHandled(Long id) {
        ContactMessage entity = find(id);
        entity.setHandled(true);
        return ContactMessageMapper.toResponse(repository.save(entity));
    }

    private ContactMessage find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ContactMessage", id));
    }
}
