import pandas as pd
from Trend.calculations import rsi_tradingview, RSI

if __name__ == "__main__":
    input_values = [
        2232.37,
        2236,
        2200.62,
        2207.51,
        2205.37,
        2201.39,
        2228.76,
        2293.6,
        2273.85,
        2273.7,
        2294.74,
        2296.39,
        2283.18,
        2270.5,
        2266.34
    ]
    df = pd.DataFrame(input_values, columns=['close'])
    rsi = rsi_tradingview(df, 14, True)
    print(rsi)
    print(rsi[-1])

    rsi = RSI(14)
    rsi.set_input_values(input_values)
    print(rsi)
    print(rsi[-1])

    input_values = [
        2194.52,
        2212.13,
        2248.41,
        2239.16,
        2212.91,
        2235.56,
        2245,
        2232.37,
        2200.62,
        2205.37,
        2228.76,
        2273.85,
        2294.74,
        2283.18,
        2266.34
    ]
    df = pd.DataFrame(input_values, columns=['close'])
    rsi = rsi_tradingview(df, 14, True)
    print(rsi)
    print(rsi[-1])

    rsi = RSI(14)
    rsi.set_input_values(input_values)
    print(rsi)
    print(rsi[-1])
