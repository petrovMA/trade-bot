from talipp.indicators import HMA
from typing import Callable, Union, List, Dict
from enum import Enum
from datetime import datetime, timedelta
from abc import ABC, abstractmethod
import math
from Trend.calculations import RSI


class TrendType(Enum):
    """Trend types"""
    Up = 1,
    Down = 2,
    Flat = 3


def serialize_trend_type(enum_value):
    if enum_value == TrendType.Up:
        return "UP"
    elif enum_value == TrendType.Down:
        return "DOWN"
    elif enum_value == TrendType.Flat:
        return "FLAT"
    else:
        return "UNKNOWN"


class ITrendDetection(ABC):
    """Describes trend detection interface"""

    @abstractmethod
    def initialize_data(self, data_list: List[float]) -> None:
        """
        Initialize trend detector by data

        :param data_list: Initial data list
        :return:
        """
        pass

    @abstractmethod
    def process_data(self, data: float) -> None:
        """
        Process new data in trend detection

        :param data: New data to process
        :return:
        """
        pass

    @abstractmethod
    def reset_data(self) -> None:
        """Reset data"""

    @abstractmethod
    def get_required_period(self) -> int:
        """Get period that will use to calculate how many data should be taken for trend detector initialization"""
        pass


class HmaTrendDetector(ITrendDetection):
    """
    Implement trend detection for the Filter №1 from technical instructions.
    Trend defines with help of 3 HMA: period of fastest > period of fast > period of slow.
    If (fastest hma > fast hma) and (fastest hma > slow hma) then it is Uptrend.
    If (fastest hma < fast hma) and (fastest hma < slow hma) then it is Downtrend.
    Otherwise, it is Flat
    """

    def __init__(self, fastest_hma_period: int, fast_hma_period: int, slow_hma_period: int):
        """
        :param fastest_hma_period:
        :param fast_hma_period:
        :param slow_hma_period:
        """

        self.__fastest_hma_period = fastest_hma_period
        self.__fast_hma_period = fast_hma_period
        self.__slow_hma_period = slow_hma_period

        self._fastest_hma = HMA(fastest_hma_period)
        self._fast_hma = HMA(fast_hma_period)
        self._slow_hma = HMA(slow_hma_period)

        self.__current_trend: Union[TrendType, None] = None

    def reset_data(self) -> None:
        self.__current_trend = None

    def get_required_period(self) -> int:
        period = max(self.__fastest_hma_period, max(self.__fast_hma_period, self.__slow_hma_period))
        period += int(math.sqrt(period)) * 2 + 3
        return period

    def initialize_data(self, data_list: List[float]):
        self._fastest_hma.set_input_values(data_list)
        self._fast_hma.set_input_values(data_list)
        self._slow_hma.set_input_values(data_list)

        self.__define_trend()

    def process_data(self, data: float) -> None:
        self._fastest_hma.add_input_value(data)
        self._fast_hma.add_input_value(data)
        self._slow_hma.add_input_value(data)
        self.__define_trend()

    def __define_trend(self) -> None:
        """
        Define current trend due to HMA filter. If trend has changed it calls handler function
        :return:
        """
        if len(self._fastest_hma) > 0 and len(self._fast_hma) > 0 and len(self._slow_hma) > 0:
            new_trend = TrendType.Flat
            if self._fastest_hma[-1] > self._fast_hma[-1] and self._fastest_hma[-1] > self._slow_hma[-1]:
                new_trend = TrendType.Up
            elif self._fastest_hma[-1] < self._fast_hma[-1] and self._fastest_hma[-1] < self._slow_hma[-1]:
                new_trend = TrendType.Down

            if (self.__current_trend is None) or (self.current_trend != new_trend):
                self.__current_trend = new_trend

    @property
    def current_trend(self) -> Union[TrendType, None]:
        """Current trend"""
        return self.__current_trend


class RsiTrendDetector(ITrendDetection):
    """Detects trends with single RSI indicator.
       Assumes, that this detector defines uptrend as RSI>50 and downtrend as RSI<50
    """

    def __init__(self, rsi_period: int):
        """

        :param rsi_period: RSI indicator period
        """

        self.__period = rsi_period
        self._rsi = RSI(rsi_period)
        self.__current_trend: Union[TrendType, None] = None

    def reset_data(self) -> None:
        self.__current_trend = None

    def get_required_period(self) -> int:
        return self.__period * 9

    def initialize_data(self, data_list: List[float]) -> None:
        self._rsi.set_input_values(data_list)
        self.__define_trend()

    def process_data(self, data: float) -> None:
        self._rsi.add_input_value(data)
        self.__define_trend()

    def __define_trend(self) -> None:
        """
        :return:
        """
        if len(self._rsi) > 0:
            new_trend = TrendType.Up if self._rsi[-1] > 50 else TrendType.Down
            if (self.__current_trend is None) or (self.current_trend != new_trend):
                self.__current_trend = new_trend

    @property
    def current_rsi(self) -> Union[float, None]:
        """Get current rsi value"""
        if len(self._rsi) > 0:
            return self._rsi[-1]
        else:
            return None

    @property
    def current_trend(self) -> Union[TrendType, None]:
        """Current trend"""
        return self.__current_trend


class CoupleRsiTrendDetector:
    """
    Trend detector based on 2 RsiTrendDetector.
    It defines uptrend if both RsiTrendDetector define trend as up.
    It defines downtrend if both RsiTrendDetector define trend as down.
    It defines flat if one RsiTrendDetector defines trend as up and another - as down.
    """

    def __init__(self, rsi_small_tf_period: int, rsi_big_tf_period: int):
        """

        :param rsi_small_tf_period: RSI period in small timeframe
        :param rsi_big_tf_period: RSI period in big timeframe
        """

        self.rsi_small_tf_detector = RsiTrendDetector(rsi_small_tf_period)
        self.rsi_big_tf_detector = RsiTrendDetector(rsi_big_tf_period)

        self.__current_trend: Union[TrendType, None] = None

    def rsi_trend_changing(self) -> None:
        """
        Handler for any of RsiTrendDetector.
        Here it defines trend for the whole detector
        :return:
        """

        small_tf_trend = self.rsi_small_tf_detector.current_trend
        big_tf_trend = self.rsi_big_tf_detector.current_trend
        if small_tf_trend is not None and big_tf_trend is not None:
            if small_tf_trend == TrendType.Up and big_tf_trend == TrendType.Up:
                new_trend = TrendType.Up
            elif small_tf_trend == TrendType.Down and big_tf_trend == TrendType.Down:
                new_trend = TrendType.Down
            else:
                new_trend = TrendType.Flat

            if (self.__current_trend is None) or (self.current_trend != new_trend):
                self.__current_trend = new_trend

    @property
    def current_trend(self) -> Union[TrendType, None]:
        """Current trend"""
        return self.__current_trend
