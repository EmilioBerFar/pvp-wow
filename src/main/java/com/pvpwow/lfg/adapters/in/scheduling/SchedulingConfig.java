package com.pvpwow.lfg.adapters.in.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the scheduler for the retention sweep.
 *
 * <p>Kept out of the application class on purpose: the scheduling capability
 * belongs to this adapter, not to the application entry point. Wiring it here
 * means the job is the only thing that needs the scheduler, and a test that
 * boots the context knows exactly where the switch lives.
 */
@Configuration
@EnableScheduling
class SchedulingConfig {
}
