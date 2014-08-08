package framian

import spire.algebra.Order

import scala.reflect.ClassTag

import org.scalacheck._
import org.scalacheck.Arbitrary.arbitrary

import scala.util.Random

trait CellGenerators {

  /** Equivalent to invoking {{{genCell(gen, (weight._1, weight._2, 0)}}}. It simply does not
    * generate [[NM]] values.
    * @see [[genCell(Gen[A], (Int, Int, Int))]]
    */
  def genCell[A](gen: Gen[A], weight: (Int, Int)): Gen[Cell[A]] =
    genCell(gen, (weight._1, weight._2, 0))

  /** Generate a cell that can produce one of [[Value]], [[NA]] or [[NM]] cells based on a given
    * weighted probability distribution.
    *
    * @param gen    The generator that generates the inner values for the [[Value]] cell
    * @param weight The weighted probability that either a [[Value]] (`_1`), [[NA]] (`_2`) or [[NM]]
    *               (`_3`) will be generated. E.g., (1, 1, 1), leaves a 33% chance for each
    * @return       A generator that will produce the provided weighted distribution of cell types
    */
  def genCell[A](gen: Gen[A], weight: (Int, Int, Int)): Gen[Cell[A]] = {
    gen.flatMap { genVal =>
      Gen.oneOf(
        List.fill(weight._1)(Value(genVal)) ++
        List.fill(weight._2)(NA) ++
        List.fill(weight._3)(NM)
        .toSeq
      )
    }
  }

}
object CellGenerators extends CellGenerators

trait ColumnGenerators extends CellGenerators {

  /** Generate a dense column, containing only [[Value]] cells.
    *
    * @param gen  The generator for the cell values
    * @return     The generator that will produce [[Column]]s
    */
  def genDenseColumn[A: ClassTag](gen: Gen[A]): Gen[Column[A]] = for {
    as <- Gen.nonEmptyListOf(gen)
  } yield Column.fromArray(as.toArray)

  /** Generate a sparse column, containing only [[Value]] and [[NA]] cells.
    *
    * @param gen  The generator for the cell values
    * @return     The generator that will produce [[Column]]s
    */
  def genSparseColumn[A](gen: Gen[A]): Gen[Column[A]] = for {
    cells <- Gen.nonEmptyListOf(genCell(gen, (9, 1)))
  } yield Column.fromCells(cells.toVector)

  /** Generate a dirty column, containing any kind of cell, including all [[NonValue]] possibilities
    * and valid [[Value]]s with the given inner type.
    *
    * @param gen  The generator for the cell values
    * @return     The generator that will produce [[Column]]s
    */
  def genDirtyColumn[A](gen: Gen[A]): Gen[Column[A]] = for {
    cells <- Gen.nonEmptyListOf(genCell(gen, (7, 2, 1)))
  } yield Column.fromCells(cells.toVector)
}
object ColumnGenerators extends ColumnGenerators

trait SeriesGenerators {

  /** Generates a [[Tuple2]] whose first and second element are generated from
    * the provided {{{firstGen}}} and {{{secondGen}}} generators.
    *
    * @param firstGen   The generator for the value of the first element of the [[Tuple2]]
    * @param secondGen  The generator for the value of the second element of the [[Tuple2]]
    * @return           The generator that will produce [[Tuple2]]s
    */
  def genTuple2[K, V](firstGen: Gen[K], secondGen: Gen[V]): Gen[(K, V)] = for {
    k <- firstGen
    v <- secondGen
  } yield (k, v)

  /** Generates a series that only contains valid [[Value]] cells as values for the series.
    *
    * @param keyGen A generator that generates all the keys for the series index
    * @param valGen A generator that generates all the values wrapped in [[Value]]s
    * @return       A generator for a series that contains only [[Value]]s
    */
  def genNonEmptyDenseSeries[K: Order: ClassTag, V: ClassTag](keyGen: Gen[K], valGen: Gen[V]): Gen[Series[K, V]] =
    for {
      t2s <- Gen.nonEmptyListOf(genTuple2(keyGen, CellGenerators.genCell(valGen, (1, 0))))
    } yield Series.fromCells(t2s: _*)

  /** Generates a series that contains arbitrary values for both key and value according to the
    * given types {{{K}}} and {{{V}}}.
    *
    * @return       A generator for a series that contains only arbitrary [[Value]]s
    */
  def genNonEmptyArbitraryDenseSeries[K: Arbitrary: Order: ClassTag, V: Arbitrary: ClassTag]: Gen[Series[K, V]] =
    genNonEmptyDenseSeries(arbitrary[K], arbitrary[V])

  /** Generates a series that contains both [[Value]] and [[NA]] cells as values.
    *
    * @param keyGen A generator that generates all the keys for the series index
    * @param valGen A generator that generates all the values wrapped in [[Value]]s
    * @return       A generator for a series that contains [[Value]]s and [[NA]]s
    */
  def genNonEmptySparseSeries[K: Order: ClassTag, V: ClassTag](keyGen: Gen[K], valGen: Gen[V]): Gen[Series[K, V]] =
    for {
      denseKey <- keyGen
      denseCell <- CellGenerators.genCell(valGen, (1, 0))
      t2s <- Gen.listOf(genTuple2(keyGen, CellGenerators.genCell(valGen, (9, 1))))
    } yield
      // Add a 2-tuple that is known to be dense to any list generated by the listOf generator. This
      // ensures we always have at least one dense cell in the series
      Series.fromCells(Random.shuffle((denseKey, denseCell) :: t2s): _*)

  /** Generates a series that contains arbitrary values for both key and value according to the
    * given types {{{K}}} and {{{V}}}.
    *
    * @return       A generator for a series that contains arbitrary [[Value]]s and [[NM]]s
    */
  def genNonEmptyArbitrarySparseSeries[K: Arbitrary: Order: ClassTag, V: Arbitrary: ClassTag]: Gen[Series[K, V]] =
    genNonEmptySparseSeries(arbitrary[K], arbitrary[V])

  /** Generates a series that contains any potential cell type as values, including [[Value]],
    * [[NA]] and [[NM]] types.
    *
    * @param keyGen  A generator that generates all the keys for the series index
    * @param valGen  A generator that generates all the values wrapped in [[Value]]s
    * @return        A generator for a series that contains [[Value]]s, [[NA]]s and [[NM]]s
    */
  def genNonEmptyDirtySeries[K: Order: ClassTag, V: ClassTag](keyGen: Gen[K], valGen: Gen[V]): Gen[Series[K, V]] =
    for {
      denseKey <- keyGen
      denseCell <- CellGenerators.genCell(valGen, (1, 0))
      t2s <- Gen.listOf(genTuple2(keyGen, CellGenerators.genCell(valGen, (7, 2, 1))))
    } yield
      // Add a 2-tuple that is known to be dense to any list generated by the listOf generator. This
      // ensures we always have at least one dense cell in the series
      Series.fromCells(Random.shuffle((denseKey, denseCell) :: t2s): _*)

  /** Generates a series that contains arbitrary values for both key and value according to the
    * given types {{{K}}} and {{{V}}}.
    *
    * @return       A generator for a series that contains arbitrary [[Value]]s, [[NA]]s and [[NM]]s
    */
  def genNonEmptyArbitraryDirtySeries[K: Arbitrary: Order: ClassTag, V: Arbitrary: ClassTag]: Gen[Series[K, V]] =
    genNonEmptyDirtySeries(arbitrary[K], arbitrary[V])
}
object SeriesGenerators extends SeriesGenerators