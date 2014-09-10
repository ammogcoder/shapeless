/*
 * Copyright (c) 2014 Miles Sabin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package shapeless

import poly._

trait SMC {
  // http://en.wikipedia.org/wiki/Symmetric_monoidal_category

  type :#:[_, _]
  type Nil

  def nil: Nil

  def intro[A](a: A): A :#: Nil
  // braiding operator
  def swap[A, B](p: A :#: B): B :#: A
  // unit coherence
  def unitRight[A](p: A :#: Nil): A
  def unitLeft[B](p: Nil :#: B): B
  // associativity coherence
  def assoc[A, B, C](p: (A :#: B) :#: C): A :#: (B :#: C)

  def leftMap[A, B, C](p: A :#: B)(f: A => C): C :#: B
  def rightMap[A, B, C](p: A :#: B)(f: B => C): A :#: C =
    swap(leftMap(swap(p))(f))
  def bimap[A, B, C, D](p: A :#: B)(fa: A => C, fb: B => D): C :#: D =
    rightMap(leftMap(p)(fa))(fb)

  def shift[A, B, C](p: (A :#: B) :#: C): B :#: A :#: C =
    assoc(leftMap(p)(swap))

  trait Uncons[P] {
    type Head
    type Tail
    def head(p: P): Head
    def tail(p: P): Tail
  }

  object Uncons {
    type Aux[P, Head0, Tail0] = Uncons[P] { type Head = Head0 ; type Tail = Tail0 }
  }

  trait LeftFolder[P, In, F] extends DepFn2[P, In]

  object LeftFolder {
    def apply[P, In, F](implicit folder: LeftFolder[P, In, F]): Aux[P, In, F, folder.Out] = folder

    type Aux[P, In, F, Out0] = LeftFolder[P, In, F] { type Out = Out0 }

    implicit def folder[P, In, F, Out0](implicit folder: LeftFolder0[P :#: Nil, In, F, P]): Aux[P, In, F, folder.Out] =
      new LeftFolder[P, In, F] {
        type Out = folder.Out
        def apply(p : P, in: In) : Out = folder(intro(p), in)
      }

    trait LeftFolder0[Elem, Acc, F, S] {
      type Out
      def apply(e: Elem, acc: Acc): Out
    }

    object LeftFolder0 {
      type Aux[Elem, Acc, F, S, Out0] = LeftFolder0[Elem, Acc, F, S] { type Out = Out0 }

      implicit def nilFolder[Rev, Acc, F, Out0]
        (implicit
          fa: Case2.Aux[F, Nil :#: Rev, Acc, Out0]
        ): Aux[Nil :#: Rev, Acc, F, Nil, Out0] =
        new LeftFolder0[Nil :#: Rev, Acc, F, Nil] {
          type Out = Out0
          def apply(elem: Nil :#: Rev, acc: Acc): Out = fa(elem, acc)
        }

      implicit def consFolder[A, B, C, Acc, F, OutA]
        (implicit
          fa: Case2.Aux[F, (A :#: B) :#: C, Acc, OutA],
          fb: LeftFolder0[B :#: A :#: C, OutA, F, B]
        ): Aux[(A :#: B) :#: C, Acc, F, A :#: B, fb.Out] =
          new LeftFolder0[(A :#: B) :#: C, Acc, F, A :#: B] {
            type Out = fb.Out
            def apply(elem: (A :#: B) :#: C, acc: Acc) : Out =
              fb(shift(elem), fa(elem, acc))
          }
    }
  }


  trait Length[P] extends DepFn1[P]

  object Length {
    def apply[P](implicit length: Length[P]): Aux[P, length.Out] = length

    import nat._0

    type Aux[P, Out0] = Length[P] { type Out = Out0 }

    implicit def length[P](implicit folder: LeftFolder[P, _0, lengthp.type]): Aux[P, folder.Out] =
      new Length[P] {
        type Out = folder.Out
        def apply(p: P): Out = folder(p, _0)
      }

    trait lengthp0 extends Poly2 {
      implicit def caseCons[Elem, N <: Nat] = at[Elem, N]((_, acc) => Succ[N])
    }
    object lengthp extends lengthp0 {
      implicit def caseNil[Rev, N <: Nat] = at[Nil :#: Rev, N]((_, acc) => acc)
    }
  }

  trait Reverse[P] extends DepFn1[P]

  object Reverse {
    def apply[P](implicit reverse: Reverse[P]): Aux[P, reverse.Out] = reverse

    type Aux[P, Out0] = Reverse[P] { type Out = Out0 }

    implicit def reverse[P](implicit folder: LeftFolder[P, Nil, reversep.type]): Aux[P, folder.Out] =
      new Reverse[P] {
        type Out = folder.Out
        def apply(p: P): Out = folder(p, nil)
      }

    trait reversep0 extends Poly2 {
      implicit def caseCons[Elem, Acc] = at[Elem, Acc]((elem, _) => elem)
    }
    object reversep extends reversep0 {
      implicit def caseNil[Rev, Acc] = at[Nil :#: Rev, Acc]((elem, _) => unitLeft(elem))
    }
  }

  trait Mapper[F, P] extends DepFn1[P]

  object Mapper {
    def apply[F, P](implicit mapper: Mapper[F, P]): Aux[F, P, mapper.Out] = mapper

    type Aux[F, P, Out0] = Mapper[F, P] { type Out = Out0 }

    implicit def nilMapper1[F]: Aux[F, Nil, Nil] =
      new Mapper[F, Nil] {
        type Out = Nil
        def apply(p : Nil): Out = p
      }

    implicit def consMapper1[F <: Poly, A, B]
      (implicit fa: Case1[F, A], mb: Mapper[F, B]): Aux[F, A :#: B, fa.Result :#: mb.Out] =
        new Mapper[F, A :#: B] {
          type Out = fa.Result :#: mb.Out
          def apply(p: A :#: B): Out = bimap(p)(fa(_), mb(_))
        }
  }
}

object SMC {
  type Aux[Cons0[_, _], Nil0] = SMC { type :#:[A, B] = Cons0[A, B] ; type Nil = Nil0 }

  implicit object PairSMC extends SMC {
    type :#:[A, B] = (A, B)
    type Nil = Unit

    def nil: Unit = ()

    def intro[A](a: A): (A, Nil) = (a, ())
    def swap[A, B](p: (A, B)): (B, A) = p.swap
    def unitRight[A](p: (A, Unit)): A = p._1
    def unitLeft[B](p: (Unit, B)): B = p._2
    def assoc[A, B, C](p: ((A, B), C)): (A, (B, C)) = (p._1._1, (p._1._2, p._2))

    def leftMap[A, B, C](p: (A, B))(f: A => C): (C, B) = (f(p._1), p._2)

    implicit def mkUncons[A, B]: Uncons.Aux[(A, B), A, B] =
      new Uncons[(A, B)] {
        type Head = A
        type Tail = B
        def head(p: (A, B)): A = p._1
        def tail(p: (A, B)): B = p._2
      }
  }

  implicit object EitherSMC extends SMC {
    // Ideally we Nil would be Nothing, however this causes implicit resolution problems as
    // described here https://issues.scala-lang.org/browse/SI-4982, namely that an appearance
    // of Nothing is treated as signalling failure.
    type :#:[A, B] = Either[A, B]
    type Nil = Unit

    def nil: Unit = ()

    def intro[A](a: A): Either[A, Nil] = Left(a)
    def swap[A, B](p: Either[A, B]): Either[B, A] = p.swap
    def unitRight[A](p: Either[A, Nil]): A = p match {
      case Left(a) => a
      case _ => ??? // Needed because we can't define Nil as Nothing
    }
    def unitLeft[B](p: Either[Nil, B]): B = p match {
      case Right(b) => b
      case _ => ??? // Needed because we can't define Nil as Nothing
    }
    def assoc[A, B, C](p: Either[Either[A, B], C]): Either[A, Either[B, C]] = p match {
      case Left(Left(a)) => Left(a)
      case Left(Right(b)) => Right(Left(b))
      case Right(c) => Right(Right(c))
    }

    def leftMap[A, B, C](p: Either[A, B])(f: A => C): Either[C, B] = p match {
      case Left(a) => Left(f(a))
      case Right(b) => Right(b)
    }

    implicit def mkUncons[A, B]: Uncons.Aux[Either[A, B], Option[A], Option[B]] =
      new Uncons[Either[A, B]] {
        type Head = Option[A]
        type Tail = Option[B]
        def head(p: Either[A, B]): Option[A] = p.left.toOption
        def tail(p: Either[A, B]): Option[B] = p.right.toOption
      }
  }
}

trait RightLeaning[T] {
  type S <: SMC
  val smc: S
}

trait LowPriorityRightLeaning {
  type Aux[T, S0] = RightLeaning[T] { type S = S0 }

  implicit def nilRightLeaning[H, Cons0[_, _], Nil0](implicit smc0: SMC.Aux[Cons0, Nil0]): Aux[Cons0[H, Nil0], smc0.type] =
    new RightLeaning[Cons0[H, Nil0]] {
      type S = smc0.type
      val smc: S = smc0
    }
}

object RightLeaning extends LowPriorityRightLeaning {
  implicit def consRightLeaning[H, T, Cons0[_, _]](implicit trl: RightLeaning[T]): Aux[Cons0[H, T], trl.S] =
    new RightLeaning[Cons0[H, T]] {
      type S = trl.S
      val smc = trl.smc
    }
}

class SMCOps[P, S <: SMC](p: P, val smc: S) {
  // Use of S#T is a workaround for failure with smc.T ... see https://issues.scala-lang.org/browse/SI-4225
  def head(implicit uncons: S#Uncons[P]): uncons.Head = uncons.head(p)
  def tail(implicit uncons: S#Uncons[P]): uncons.Tail = uncons.tail(p)

  def length(implicit length: S#Length[P]): length.Out = length(p)
  def reverse(implicit reverse: S#Reverse[P]): reverse.Out = reverse(p)
  def map(f: Poly)(implicit mapper: S#Mapper[f.type, P]): mapper.Out = mapper(p)
  def foldLeft[Z](z: Z)(f: Poly2)(implicit folder: S#LeftFolder[P, Z, f.type]): folder.Out = folder(p, z)
}

object SMCOps {
  implicit def mkOps[P](p: P)(implicit rl: RightLeaning[P]): SMCOps[P, rl.S] =
    new SMCOps[P, rl.S](p, rl.smc)
}

object Demo {
  import SMCOps._
  import nat.{ _0, _1, _2, _3 }

  def typed[T](t: => T): Unit = ()

  object option extends (Id ~> Option) {
    def apply[T](t : T) = Option(t)
  }

  implicitly[RightLeaning[(Boolean, Unit)]]
  implicitly[RightLeaning[(Int, (Boolean, Unit))]]
  implicitly[RightLeaning[(String, (Int, (Boolean, Unit)))]]
  implicitly[RightLeaning[Either[Boolean, Unit]]]
  implicitly[RightLeaning[Either[Int, Either[Boolean, Unit]]]]
  implicitly[RightLeaning[Either[String, Either[Int, Either[Boolean, Unit]]]]]

  val p = ("foo", (23, (true, ())))
  val pSmc = p.smc
  typed[SMC.Aux[Tuple2, Unit]](pSmc)
  val ph = p.head
  typed[String](ph)
  val pth = p.tail.head
  typed[Int](pth)
  val ptth = p.tail.tail.head
  typed[Boolean](ptth)
  val pttt = p.tail.tail.tail
  typed[Unit](pttt)
  // val pttth = p.tail.tail.tail.head // shouldn't compile
  val plen = SMC.PairSMC.Length[(String, (Int, (Boolean, Unit)))]
  val pl = p.length
  typed[_3](pl)
  val prev = p.reverse
  typed[(Boolean, (Int, (String, Unit)))](prev)
  val popt = p.map(option)
  typed[(Option[String], (Option[Int], (Option[Boolean], Unit)))](popt)

  val c: Either[String, Either[Int, Either[Boolean, Unit]]] = Left("foo")
  val cSmc = c.smc
  typed[SMC.Aux[Either, Unit]](cSmc)
  val ch = c.head
  typed[Option[String]](ch)
  val cth = c.tail.flatMap(_.head)
  typed[Option[Int]](cth)
  val ctth = c.tail.flatMap(_.tail.flatMap(_.head))
  typed[Option[Boolean]](ctth)
  val cttt = c.tail.flatMap(_.tail.flatMap(_.tail))
  typed[Option[Unit]](cttt)
  //val cttth = c.tail.flatMap(_.tail.flatMap(_.tail.flatMap(_.head))) // shouldn't compile
  val clen = implicitly[SMC.EitherSMC.Length.Aux[Either[String, Either[Int, Either[Boolean, Unit]]], _3]]
  val cl = c.length
  typed[_3](cl)
  val crev = c.reverse
  typed[Either[Boolean, Either[Int, Either[String, Unit]]]](crev)
  val copt = c.map(option)
  typed[Either[Option[String], Either[Option[Int], Either[Option[Boolean], Unit]]]](copt)
}