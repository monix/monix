module.exports = function(grunt) {
    // Project configuration.
    grunt.initConfig({
        pkg: grunt.file.readJSON('package.json'),
        uglify: {
            options: {
                banner: '/*! <%= pkg.name %> <%= grunt.template.today("yyyy-mm-dd") %> */\n'
            },
            build: {
                src: 'build/all.js',
                dest: 'build/all.min.js'
            }
        },
        ts: {
            dev: {                                 // a particular target
                src: ["src/**/*.ts"],              // The source typescript files, http://gruntjs.com/configuring-tasks#files
                html: ["src/**/*.tpl.html"],       // The source html files, https://github.com/basarat/grunt-ts#html-2-typescript-support
                reference: "./src/reference.ts",   // If specified, generate this file that you can use for your reference management
                out: 'build/monifu-dev.js',            // If specified, generate an out.js file which is the merged js file
                outDir: 'build/',                  // If specified, the generate javascript files are placed here. Only works if out is not specified
                watch: 'src',                      // If specified, watches this directory for changes, and re-runs the current target
                options: {                         // use to override the default options, http://gruntjs.com/configuring-tasks#options
                    target: 'es3',                 // 'es3' (default) | 'es5'
                    module: 'amd',                 // 'amd' (default) | 'commonjs'
                    sourceMap: true,               // true (default) | false
                    declaration: false,            // true | false (default)
                    removeComments: false          // true (default) | false
                },
            },
            build: {                               // another target
                src: ["src/**/*.ts"],
                out: 'build/monifu.js',
                options: {                         // override the main options for this target
                    sourceMap: true,
                }
            },
        },
        /*
        typescript: {
            base: {       // fix here
                src: ['src/ * * /*.ts'],
                dest: 'build/',
                options: {
                    module: 'amd', //or commonjs
                    target: 'es3', //or es3
                    base_path: 'src/',
                    sourcemap: true,
                    declaration: true
                }
            }
        },
        */
        watch: {
            scripts: {
                files: ['src/**/*.ts'],
                tasks: ['typescript'],
                options: {
                    spawn: false,
                    livereload: true
                },
            },
        }
    });

    grunt.loadNpmTasks('grunt-contrib-uglify');
    //grunt.loadNpmTasks('grunt-typescript');
    grunt.loadNpmTasks('grunt-contrib-watch');
    grunt.loadNpmTasks("grunt-ts");

    // Default task(s).
    grunt.registerTask('default', ['typescript', 'uglify']);
};
