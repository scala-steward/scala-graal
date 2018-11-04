package japgolly.scalagraal

import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger
import org.graalvm.polyglot.{Context, Engine}
import scala.concurrent.Future
import scala.concurrent.JavaConversions.asExecutionContext

// TODO add shutdown
trait ContextAsync {
  def apply[A](f: Context => A): Future[A]
  def withAround(f: ContextAsync.Around): ContextAsync
}

object ContextAsync {

  def fixedPool(poolSize: Int): ContextBuilder[ContextAsync] =
    new ContextBuilder[ContextAsync] {
      override def apply(newContext: => Context): ContextAsync = {
        val poolNo = poolCount.getAndIncrement()
        val threadCount = new AtomicInteger(1)
        fixedPool(poolSize, DefaultContextThread(poolNo, threadCount, newContext, _))
      }
    }

  def fixedPool(poolSize: Int, createNewThread: Runnable => ContextThread): ContextAsync = {
    val threadFactory = new ThreadFactory {
      override def newThread(r: Runnable) = createNewThread(r)
    }

    val executor = new ThreadPoolExecutor(
      poolSize, poolSize,
      0L, TimeUnit.MILLISECONDS,
      new LinkedBlockingQueue[Runnable],
      threadFactory)

    executor.prestartAllCoreThreads()

    new ExecutorServiceBased(executor, Around.id)
  }

  trait ContextBuilder[A] {
    def apply(newContext: => Context): A

    def apply(lang: String): A =
      withLangauges(lang)

    def withLangauges(langs: String*): A =
      withEngine(e => Context.newBuilder(langs: _*).engine(e).build())

    def withEngine(f: Engine => Context): A = {
      val e = Engine.newBuilder().build()
      apply(f(e))
    }

    // TODO def withThreadConfig(f: Thread => Unit): ContextBuilder[A] =
  }

  trait ContextThread extends Thread {
    def contextSync: ContextSync
  }

  // ===================================================================================================================

  private val poolCount = new AtomicInteger(1)
  private def threadGroup = new ThreadGroup("scalagraal")

  private class DefaultContextThread(init: Runnable, name: String) extends Thread(threadGroup, init, name) with ContextThread {
    setDaemon(true)

    private[DefaultContextThread] var _contextSync: ContextSync = null
    override def contextSync = _contextSync
  }

  private object DefaultContextThread {
    def apply(poolNo: Int, poolThreads: AtomicInteger, newContext: => Context, init: Runnable): DefaultContextThread = {
      val init2 = new Runnable {
        override def run(): Unit = {
          val self = Thread.currentThread().asInstanceOf[DefaultContextThread]
          self._contextSync = ContextSync.single(newContext, mutex = false)
          init.run()
        }
      }
      val name = s"ScalaGraal-pool-$poolNo-thread-${poolThreads.getAndIncrement()}"
      new DefaultContextThread(init2, name)
    }
  }

  private class ExecutorServiceBased(es: ExecutorService, around: Around) extends ContextAsync {
    private[this] implicit val ec = asExecutionContext(es)

    override def apply[A](f: Context => A): Future[A] =
      Future {
        val t = Thread.currentThread().asInstanceOf[ContextThread]
        t.contextSync(around(_, f))
      }

    override def withAround(f: Around): ContextAsync =
      new ExecutorServiceBased(es, around.insideOf(f))
  }

  // ===================================================================================================================

  type Around = ContextSync.Around
  val Around = ContextSync.Around
}