/// <reference path="../src/ts-definitions/DefinitelyTyped/jquery/jquery.d.ts" />
declare module monifu {
    interface Monad<T> {
        map<B>(f: (e: T) => B): Monad<B>;
        flatMap<B>(f: (e: T) => Monad<B>): Monad<B>;
        filter(f: (e: T) => boolean): Monad<T>;
    }
}
declare module monifu {
    class Option<T> implements monifu.Monad<T> {
        private value;
        constructor(value: T);
        public get(): T;
        public getOrElse(cb: () => T): T;
        public isEmpty(): boolean;
        public nonEmpty(): boolean;
        public size(): number;
        public map<B>(f: (e: T) => B): Option<B>;
        public flatMap<B>(f: (e: T) => Option<B>): Option<B>;
        public filter(f: (e: T) => boolean): Option<T>;
        static of<A>(value: A): Option<A>;
        static some<A>(value: A): Option<A>;
        static none<A>(): Option<A>;
    }
}
declare module monifu {
}
