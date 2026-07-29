package com.example.demo.signing;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SignerSlotRepository extends JpaRepository<SignerSlot, String> {

    Optional<SignerSlot> findByToken(String token);
}
