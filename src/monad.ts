module monifu {

  export interface Monad<T> {
    map<B>(f: (e:T) => B): Monad<B>;
    flatMap<B>(f: (e:T) => Monad<B>): Monad<B>;
    filter(f: (e:T) => boolean): Monad<T>;
  }
}