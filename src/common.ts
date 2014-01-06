module monifu {

  export interface Monad<T> {
    map<B>(f: (e:T) => B): Monad<B>;
    flatMap<B>(f: (e:T) => Monad<B>): Monad<B>;
    filter(f: (e:T) => boolean): Monad<T>;
  }

  export interface Iterable<T> {
    foreach(f: (e:T) => void): void;
  }

  export interface Foldable<T> {
    foldLeft<R>(initial: R): (folder: (result: R, elem: T) => R) => R
  }
}