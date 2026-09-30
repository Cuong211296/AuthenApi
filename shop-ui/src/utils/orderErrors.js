/** True when a failed GET /orders/:code means "no such order" (ORDER_NOT_FOUND 2010 or HTTP 404), not a transient failure. */
export function isOrderNotFound(err) {
  return err?.code === 2010 || err?.status === 404;
}
