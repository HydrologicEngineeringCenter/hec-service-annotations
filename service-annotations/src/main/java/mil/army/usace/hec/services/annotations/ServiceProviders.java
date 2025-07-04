/*
 * Copyright (c) 2018.  Hydrologic Engineering Center (HEC).
 * United States Army Corps of Engineers
 * All Rights Reserved.  HEC PROPRIETARY/CONFIDENTIAL.
 * Source may not be released without written approval
 * from HEC
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package mil.army.usace.hec.services.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Similar to {@link ServiceProvider} but permits multiple registrations of one class.
 *
 * @author Shannon Newbold (sjnewbold@rmanet.com)
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface ServiceProviders
{

	/**
	 * List of service provider registrations.
	 */
	ServiceProvider[] value();

}
