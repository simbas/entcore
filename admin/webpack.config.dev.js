const webpack = require('webpack')
const webpackMerge = require('webpack-merge')

const ExtractTextPlugin = require('extract-text-webpack-plugin')
const commonConfig = require('./webpack.config.common.js')
const path_prefix = './admin/src/main'

module.exports = webpackMerge(commonConfig, {
    devtool: 'eval-source-map',

    entry: {
        'admin': path_prefix + '/ts/main.ts',
        'vendor': path_prefix + '/ts/libs/vendor.ts'
    },

    module: {
        rules: [
            {
                test: /\.scss$/,
                use: ExtractTextPlugin.extract({
                    use: ['css-loader', 'sass-loader'],
                    allChunks: true
                })
            },
            {
                test: /\.ts$/,
                use: [
                    'ts-loader',
                    'angular-router-loader',
                    'angular2-template-loader?keepUrl=true'
                ]
            },
            {
                test: /\.html$/,
                use: 'file-loader?name=templates/[name].[ext]'
            }
        ]
    },

    plugins: [
        new webpack.optimize.CommonsChunkPlugin({
            name: ['admin', 'vendor', 'polyfills']
        }),
        new ExtractTextPlugin('css/admin.css')
    ],

    devServer: require('./webpack.config.devserver.js')
})