package opendap.metacat;

import java.util.Calendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opendap.metacat.Equivalence.Components;

/**
 * This class holds the 'rules' for recognizing dates in the patterns formed
 * by the URL parse. The sole public method, classifyPotentialDate() takes
 * an Equivalence object, looks at the values associated with the pattern and
 * may add one or more DatePart values to the Equivalence object's date
 * classification information.
 * 
 * @author jimg
 *
 */
public class DateClassification {
	
    private static Logger log = LoggerFactory.getLogger(DateClassification.class);

    private final static int minimumYear = 1970;
	private final static int minimumDayNum = 0;
	private final static int maximumDayNum = 366;
	private final static int minimumMonth = 0;
	private final static int maximumMonth = 12;
	private final static int minimumDay = 0;
	private final static int maximumDay = 31;
	private final static int currentYear = Calendar.getInstance().get(Calendar.YEAR);
	private final static int maximumHour = 23;
	private final static int maximumMinute = 59;
	private final static int maximumSecond = 59;
	
	/**
	 * Provide names for the kinds of dates an Equivalence can hold
	 * @author jimg
	 *
	 */
	public static enum DatePart {
		none {
			public String toString() { return "none"; }
		},
		year {
			public String toString() { return "year"; }
		},
		year2 {
			public String toString() { return "year, two digit"; }
		},
		month {
			public String toString() { return "month"; }
		},
		month1 {
			public String toString() { return "month, one digit"; }
		},
		day {
			public String toString() { return "day"; }
		},
		daynum {
			public String toString() { return "daynum"; }
		},
		hours {
			public String toString() { return "hours"; }
		},
		minutes {
			public String toString() { return "minutes"; }
		},
		seconds {
			public String toString() { return "seconds"; }
		}
	}

	public static void classifyPotentialDate(Equivalence e) {
		if (isYear(e))
			e.addDateClassification(DatePart.year);
		else if (isMonth(e))
			e.addDateClassification(DatePart.month);
		else if (isMonth2(e))
			e.addDateClassification(DatePart.month1);
		else if (isDay(e))
			e.addDateClassification(DatePart.day);
		else if (isDayNum(e))
			e.addDateClassification(DatePart.daynum);
		else if (isYearMonthDay(e)) {
			e.addDateClassification(DatePart.year);
			e.addDateClassification(DatePart.month);
			e.addDateClassification(DatePart.day);
		}
		else if (isYearDayNumTime(e)) {
			e.addDateClassification(DatePart.year);
			e.addDateClassification(DatePart.daynum);
			e.addDateClassification(DatePart.hours);
			e.addDateClassification(DatePart.minutes);
			e.addDateClassification(DatePart.seconds);
		}
		else if (isYearDayNum(e)) {
			e.addDateClassification(DatePart.year);
			e.addDateClassification(DatePart.daynum);
		}
		else if (isYearDayNum2Time(e)) {
			e.addDateClassification(DatePart.year2);
			e.addDateClassification(DatePart.daynum);
			e.addDateClassification(DatePart.hours);
			e.addDateClassification(DatePart.minutes);
			e.addDateClassification(DatePart.seconds);
		}
		else if (isYearDayNum2(e)) {
			e.addDateClassification(DatePart.year2);
			e.addDateClassification(DatePart.daynum);
		}
		else if (isYearMonth(e)) {
			e.addDateClassification(DatePart.year);
			e.addDateClassification(DatePart.month);
		}
	}
	
	private static boolean isYear(Equivalence e) {
		log.debug("Is this a year? (" + e.getComponentValue() + ")");
		// If the pattern isn't right, fail without looking at values
		if (!e.getComponentValue().equals("dddd"))
			return false;

		// If there are any values outside the allowed range, fail
		Components c = e.getComponents();
		for (String sv: c) {
			log.debug("Is '" + sv +"' a valid year?");
			if (!isValidYear(sv))
				return false;			
		}
		/*
		ComponentsEnumeration c = e.getComponents();
		while (c.hasMoreElements()) {
			String sv = c.nextElement();
			log.debug("Is '" + sv +"' a valid year?");
			if (!isValidYear(sv))
				return false;
		}
		*/
		// If the pattern is OK and the values are all OK, return true
		return true;
	}

	private static boolean isDayNum(Equivalence e) {
		// The pattern must match and there are only a certain number of
		// discrete values possible, bounded by the number of days in the year.
		// This is not testing the values, just that the number of values
		// found is sane.
		if (!e.getComponentValue().equals("ddd") || !(e.getNumberOfValues() == 366 || e.getNumberOfValues() == 365))
			return false;

		Components c = e.getComponents();
		for (String sv: c) {
			if (!isValidDayNum(sv))
				return false;
		}

		return true;
	}

	private static boolean isDay(Equivalence e) {
		if (!e.getComponentValue().equals("dd") || e.getNumberOfValues() != 31)
			return false;

		Components c = e.getComponents();
		for (String sv: c) {
			if (!isValidDay(sv))
				return false;
		}
		
		return true;
	}

	private static boolean isMonth(Equivalence e) {
		// Test for two cases for the number of values: dd is always used (01
		// to 12 or 00 to 11) or dd or d is used. In the later case there will
		// likely be only three values that show up (10, 11 and 12).
		if (!e.getComponentValue().equals("dd") || !(e.getNumberOfValues() == 12 || e.getNumberOfValues() == 3))
			return false;

		Components c = e.getComponents();
		for (String sv: c) {
			if (!isValidMonth(sv))
				return false;
		}
		
		return true;
	}

	// Should this be here? This should be used in context when the preceding
	// Equivalence is a year.
	private static boolean isMonth2(Equivalence e) {
		// There's no point in test the number of discrete values since 0-9 are
		// all that's possible and all are valid when a single digit is used
		// for Jan to Sept./Oct.
		if (!e.getComponentValue().equals("d") || !(e.getNumberOfValues() == 10 || e.getNumberOfValues() == 9))
			return false;

		Components c = e.getComponents();
		for (String sv: c) {
			if (!isValidMonth(sv))
				return false;
		}
		
		return true;
	}
	
	private static boolean isYearMonthDay(Equivalence e) {
		if (!e.getComponentValue().equals("dddddddd")) // Eight digits
			return false;

		Components c = e.getComponents();
		for (String sv: c) {
			String ysv = sv.substring(0, 4);
			if (!isValidYear(ysv))
				return false;
			String msv = sv.substring(4, 6);
			if (!isValidMonth(msv))
				return false;
			String dsv = sv.substring(6);
			if (!isValidDay(dsv))
				return false;
		}
		
		return true;
	}

	private static boolean isYearMonth(Equivalence e) {
		if (!e.getComponentValue().equals("dddddd")) // Six digits
			return false;

		Components c = e.getComponents();
		for (String sv: c) {
			String ysv = sv.substring(0, 4);
			if (!isValidYear(ysv))
				return false;
			String msv = sv.substring(4);
			if (!isValidMonth(msv))
				return false;
		}
		
		return true;
	}

	private static boolean isYearDayNum(Equivalence e) {
		if (!e.getComponentValue().equals("ddddddd")) // 7 digits
			return false;

		Components c = e.getComponents();
		for (String sv: c) {
			String ysv = sv.substring(0, 4);
			if (!isValidYear(ysv))
				return false;
			String dnsv = sv.substring(4, 7);
			if (!isValidDayNum(dnsv))
				return false;
		}
		
		return true;
	}
	
	private static boolean isYearDayNumTime(Equivalence e) {
		if (!e.getComponentValue().equals("ddddddddddddd")) // 7 digits followed by 6 digits
			return false;

		Components c = e.getComponents();
		for (String sv: c) {
			String ysv = sv.substring(0, 4);
			if (!isValidYear(ysv))
				return false;
			String dnsv = sv.substring(4, 7);
			if (!isValidDayNum(dnsv))
				return false;
			String hsv = sv.substring(7, 9);
			if (!isValidHour(hsv))
				return false;
			String msv = sv.substring(9, 11);
			if (!isValidMinute(msv))
				return false;
			String ssv = sv.substring(11);
			if (!isValidSecond(ssv))
				return false;			
		}
		
		return true;
	}

	private static boolean isYearDayNum2(Equivalence e) {
		if (!e.getComponentValue().equals("ddddddddddd")) // 5 digits (two for year) followed by 6 digits
			return false;

		Components c = e.getComponents();
		for (String sv: c) {
			String ysv = sv.substring(0, 2);
			if (!isValidYear2(ysv))
				return false;
			String dnsv = sv.substring(2, 4);
			if (!isValidDayNum(dnsv))
				return false;
		}
		
		return true;
	}

	private static boolean isYearDayNum2Time(Equivalence e) {
		if (!e.getComponentValue().equals("ddddddddddd")) // 5 digits (two for year) followed by 6 digits
			return false;

		Components c = e.getComponents();
		for (String sv: c) {
			String ysv = sv.substring(0, 2);
			if (!isValidYear2(ysv))
				return false;
			String dnsv = sv.substring(2, 5);
			if (!isValidDayNum(dnsv))
				return false;
			String hsv = sv.substring(5, 7);
			if (!isValidHour(hsv))
				return false;
			String msv = sv.substring(7, 9);
			if (!isValidMinute(msv))
				return false;
			String ssv = sv.substring(9);
			if (!isValidSecond(ssv))
				return false;
		}
		
		return true;
	}

	private static boolean isValidDay(String sv) {
		int value = new Integer(sv).intValue();
		return !(value < minimumDay || value > maximumDay);
	}

	private static boolean isValidDayNum(String sv) {
		int value = new Integer(sv).intValue();
		return !(value < minimumDayNum || value > maximumDayNum);
	}

	private static boolean isValidMonth(String sv) {
		int value = new Integer(sv).intValue();
		return !(value < minimumMonth || value > maximumMonth);
	}

	private static boolean isValidYear(String sv) {
		int value = new Integer(sv).intValue();
		return !(value < minimumYear || value > currentYear);
	}

	private static boolean isValidYear2(String sv) {
		int value = new Integer(sv).intValue();
		return !(value > (currentYear/100) && value < (minimumYear/100));
	}

	private static boolean isValidHour(String sv) {
		int value = new Integer(sv).intValue();
		return !(value < 0|| value > maximumHour);
	}

	private static boolean isValidMinute(String sv) {
		int value = new Integer(sv).intValue();
		return !(value < 0 || value > maximumMinute);
	}

	private static boolean isValidSecond(String sv) {
		int value = new Integer(sv).intValue();
		return !(value < 0 || value > maximumSecond);
	}

}
