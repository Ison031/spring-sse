package com.ison.sse.com.ison.sse.repository;

import com.ison.sse.model.Notification;
import org.springframework.data.repository.CrudRepository;

import javax.transaction.Transactional;

public interface NotificationRepository extends CrudRepository<Notification, Long> {

}
