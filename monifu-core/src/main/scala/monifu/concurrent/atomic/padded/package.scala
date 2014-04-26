package monifu.concurrent.atomic

/**
 * Atomic classes that are cache-padded for reducing cache contention,
 * until JEP 142 and `@Contended` happens. See:
 *
 * http://mail.openjdk.java.net/pipermail/hotspot-dev/2012-November/007309.html
 */
package object padded extends Implicits.Level3