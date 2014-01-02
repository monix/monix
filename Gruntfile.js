module.exports = function(grunt) {
    // Project configuration.
    grunt.initConfig({
        pkg: grunt.file.readJSON('package.json'),
        uglify: {
            options: {
                banner: '/*! <%= pkg.name %> <%= grunt.template.today("yyyy-mm-dd") %> */\n'
            },
            build: {
                src: 'src/all.js',
                dest: 'build/all.min.js'
            }
        },
        typescript: {
            base: {
                src: ['src/**/*.ts'],
                dest: 'build/',
                options: {
                    module: 'amd', //or commonjs
                    target: 'es3', //or es3
                    base_path: 'src/',
                    sourcemap: true,
                    declaration: true
                }
            }
        }
    });

    grunt.loadNpmTasks('grunt-contrib-uglify');
    grunt.loadNpmTasks('grunt-typescript');

    // Default task(s).
    grunt.registerTask('default', ['typescript', 'uglify']);
};
