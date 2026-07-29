package com.example.demo.signing;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DraftRepository extends JpaRepository<Draft, String> {

    List<Draft> findAllByOrderByCreatedAtDesc();
}
