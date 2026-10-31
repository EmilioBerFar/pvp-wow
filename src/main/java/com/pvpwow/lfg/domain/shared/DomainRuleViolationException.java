package com.pvpwow.lfg.domain.shared;

/**
 * Thrown when a domain rule is violated.
 *
 * <p>Domain objects are born valid: the only way to create them is through
 * constructors and factories that enforce their own rules. If a rule is broken,
 * the object refuses to exist and this exception explains why.
 */
public class DomainRuleViolationException extends RuntimeException {

    public DomainRuleViolationException(String message) {
        super(message);
    }
}
