package com.eventhub.eventhub_backend.repository;

import com.eventhub.eventhub_backend.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {}
