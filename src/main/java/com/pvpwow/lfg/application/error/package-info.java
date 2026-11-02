/**
 * Application-level failures that are not domain rule violations: a requested
 * resource does not exist, or the caller is not allowed to act on it. The
 * inbound adapter decides their HTTP status; the application never knows HTTP.
 */
package com.pvpwow.lfg.application.error;
