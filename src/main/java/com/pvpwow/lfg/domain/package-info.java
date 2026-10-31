/**
 * Pure domain model: aggregates, entities, value objects and domain services.
 *
 * <p>Dependency rule: this package and everything under it must have ZERO framework
 * dependencies — no Spring, no JPA, no JSON, no HTTP. The domain must compile and
 * pass its tests on its own. If you need such an import here, the design is wrong.
 */
package com.pvpwow.lfg.domain;
