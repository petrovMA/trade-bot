from flask import Flask, jsonify, request
from talipp.indicators import HMA
from Trend.TrendDetection import RsiTrendDetector, TrendType, CoupleRsiTrendDetector, HmaTrendDetector, serialize_trend_type, RSI

app = Flask(__name__)


@app.route('/calc_trend', methods=['POST'])
def create_message():
    message = request.json
    rsi_small_tf_period = message['rsiSmallPeriod']
    rsi_big_tf_period = message['rsiBigPeriod']
    tf1_candlesticks = message['tf1_candlesticks']
    tf2_candlesticks = message['tf2_candlesticks']
    tf3_candlesticks = message['tf3_candlesticks']
    fastest_hma_period = message['fastest_hma_period']
    fast_hma_period = message['fast_hma_period']
    slow_hma_period = message['slow_hma_period']

    rsi_detector = CoupleRsiTrendDetector(rsi_small_tf_period, rsi_big_tf_period)
    rsi_detector.rsi_small_tf_detector.initialize_data(tf1_candlesticks)
    rsi_detector.rsi_big_tf_detector.initialize_data(tf2_candlesticks)

    hma_detector = HmaTrendDetector(fastest_hma_period, fast_hma_period, slow_hma_period)
    hma_detector.initialize_data(tf3_candlesticks)

    return jsonify(
        {
            "hma_trend": serialize_trend_type(hma_detector.current_trend),
            "hma_fastest_hma": hma_detector._fastest_hma[-1],
            "hma_fast_hma": hma_detector._fast_hma[-1],
            "hma_slow_hma": hma_detector._slow_hma[-1],
            "rsi_small_tf_detector": serialize_trend_type(rsi_detector.rsi_small_tf_detector.current_trend),
            "rsi_big_tf_detector": serialize_trend_type(rsi_detector.rsi_big_tf_detector.current_trend),
            "rsi_small_tf_detector_current_rsi": rsi_detector.rsi_small_tf_detector.current_rsi,
            "rsi_big_tf_detector.current_rsi": rsi_detector.rsi_big_tf_detector.current_rsi
        }
    ), 200


@app.route('/hma', methods=['POST'])
def calc_hma():
    message = request.json
    data_list = message['data_list']
    hma_period = message['hma_period']

    hma = HMA(hma_period)

    hma.set_input_values(data_list)

    return jsonify(
        {
            "hma": hma[-1],
            "hma_period": hma_period
        }
    ), 200


@app.route('/rsi', methods=['POST'])
def calc_rsi():
    message = request.json
    data_list = message['data_list']
    rsi_period = message['rsi_period']

    rsi = RSI(rsi_period)

    rsi.set_input_values(data_list)

    return jsonify(
        {
            "rsi": rsi[-1],
            "rsi_period": rsi_period
        }
    ), 200


@app.route('/up')
def hello():
    rsi_detector = CoupleRsiTrendDetector(14, 14)
    rsi_detector.rsi_small_tf_detector.initialize_data(
        [2019.79, 2122.92, 2123.00, 2104.64, 2098.14, 2091.11, 2089.42, 2079.10, 2047.79, 2056.58, 2059.53, 2069.32,
         2077.46, 2054.52, 2052.89, 2058.90])
    rsi_detector.rsi_big_tf_detector.initialize_data([
        1780.02, 1776.76, 1795.49, 1809.81, 1815.67, 1848.20, 1800.68, 1832.49,
        1856.08, 1892.49, 1901.71, 1885.79, 1888.42, 2122.92, 2079.10, 2054.52
    ])
    rsi_detector.rsi_trend_changing()
    return f'current_trend = {rsi_detector.current_trend}</br>' \
           f'rsi_small_tf_detector = {rsi_detector.rsi_small_tf_detector.current_trend}</br>' \
           f'rsi_big_tf_detector = {rsi_detector.rsi_big_tf_detector.current_trend}</br>' \
           f'rsi_small_tf_detector.current_rsi = {rsi_detector.rsi_small_tf_detector.current_rsi}</br>' \
           f'rsi_big_tf_detector.current_rsi = {rsi_detector.rsi_big_tf_detector.current_rsi}</br>'


@app.route('/message/<string:key>', methods=['GET'])
def get_message(key):
    # Retrieve a message
    return jsonify({key: key}), 200


if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0', port=5000)
