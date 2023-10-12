/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient

import android.accounts.Account

class InvalidAccountException(account: Account): Exception("Invalid account: $account")