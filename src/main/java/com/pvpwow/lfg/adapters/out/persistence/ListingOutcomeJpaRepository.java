package com.pvpwow.lfg.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ListingOutcomeJpaRepository extends JpaRepository<ListingOutcomeEntity, UUID> {
}
