package com.msme.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.msme.entity.Msme;

@Repository
public interface MsmeRepository
        extends JpaRepository<Msme, Long> {

    /*
     * Returns all MSMEs assigned to a particular RM.
     *
     * This will be used for:
     *
     * 1. RM's MSME dropdown while creating an MSME representative.
     *
     * 2. Determining which MSME representatives belong
     *    to the logged-in RM.
     */
    List<Msme> findByAssignedRmId(Long assignedRmId);
}