import scala.concurrent.{ExecutionContext, Future, Promise}

package object utils {

  def first[T](f: Future[T], g: Future[T])(implicit executor: ExecutionContext): Future[T] = {
    val p = Promise[T]
    p.completeWith(Future.firstCompletedOf(Seq(f.fallbackTo(g), g.fallbackTo(f))))
    p.future
  }

  implicit class FutureOps(f: scala.concurrent.Future.type ) {
    def any[A, M[X] <: TraversableOnce[X]](in: M[Future[A]])(implicit executor: ExecutionContext): Future[A] = {
      in.fold(Future.failed(new RuntimeException("Input was an empty list or all of the futures failed")))(first)
    }
  }

}
