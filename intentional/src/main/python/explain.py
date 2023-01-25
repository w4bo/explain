from sklearn.metrics import mean_squared_error, r2_score
from sklearn.model_selection import KFold
import pandas as pd
import numpy as np
import argparse
import warnings
import json
import sys

###############################################################################
# PARAMETERS SETUP
###############################################################################

parser = argparse.ArgumentParser()
parser.add_argument("--path", help="where to put the output", type=str)
parser.add_argument("--file", help="the file name", type=str)
parser.add_argument("--explain", help="the measure to explain", type=str)
parser.add_argument("--intention", help="the intention json string")
args = parser.parse_args()

path = args.path.replace("\"", "")
file = args.file
measure_to_explain = args.explain.lower()
intention = args.intention.replace("__", " ")
intention = json.loads(intention)

# fitted models
fitting_component_table = pd.DataFrame(columns=["Component", "Interest", "Property", "Equation"])
# list of all measures
measures = [measure["MEA"].lower() for measure in intention["MC"]]


###############################################################################
# FUNCTIONS DEFINITION
###############################################################################


def compute_correlation(df, model):
    component_table = pd.DataFrame(columns=["Component", "Interest", "Property"])
    correlations_df = df.corrwith(df[measure_to_explain], method=model, numeric_only=True).round(3)
    correlations_df = correlations_df.drop(labels=[measure_to_explain]).sort_values(ascending=False)

    for index, value in correlations_df.items():
        component_table.loc[len(component_table.index)] = [
            '(' + measure_to_explain + ', ' + index + ')', value, model]
    
    return component_table


def compute_pairs_to_fit():
    pairs = []

    for elem in measures:
        if elem != measure_to_explain:
            pairs.append([measure_to_explain, elem])
    
    return pairs


# Convert model coefficients into a polynomial string
def equation_print(z, x='x'):
    c = "{:.22f}".format(float(z[0]))
    z = z[1:]
    return c + (
        '' if len(z) == 0 else (x if len(z) == 1 else x + '^' + str(len(z))) +
                               ('' if "{:.22f}".format(float(z[0])).startswith('-') else '+') + equation_print(z, x=x)
    )


def equation_reduced_print(z, x='x'):
    c = "{:.2f}".format(float(z[0]))
    z = z[1:]
    return c + (
        '' if len(z) == 0 else (x if len(z) == 1 else x + '^' + str(len(z))) +
                               ('' if "{:.2f}".format(float(z[0])).startswith('-') else '+') + equation_reduced_print(z, x=x)
    )


def sort_arrays(array_x, array_y):
    sort_indices = array_x.argsort()
    return array_x[sort_indices], array_y[sort_indices]


def compute_k_flod_cv(x, y, degrees, num_folds, seed):
    best_mse = float('+inf')
    best_r2 = float('-inf')
    best_model = None

    # initialize KFold
    kf = KFold(n_splits=num_folds, random_state=seed, shuffle=True)
    # loop over degrees from 0 to 5
    for degree in range(degrees + 1):
        mse_list = []
        r2_list = []
        models = []

        # loop over the folds
        for train_index, test_index in kf.split(x):
            x_train, x_test = x[train_index], x[test_index]
            y_train, y_test = y[train_index], y[test_index]
            if len(x_train) < degree + 1:
                break

            # sort train and test set
            x_train, y_train = sort_arrays(x_train, y_train)
            x_test, y_test = sort_arrays(x_test, y_test)

            # fit polynomial to training data
            coefficients = np.polyfit(x_train, y_train, degree)
            model = np.poly1d(coefficients)
            # generate predictions on test data
            y_pred = model(x_test)

            # calculate mean squared error for this fold
            if (len(y_test) - degree - 1) < 1:
                mse = mean_squared_error(y_test, y_pred)
            else:
                mse = (mean_squared_error(y_test, y_pred) * (len(y_test)) / (len(y_test) - degree - 1))

            # calculate R^2 score for this fold
            with warnings.catch_warnings():
                warnings.filterwarnings('error')
                try:
                    r2 = r2_score(y_test, y_pred)
                except Warning:
                    r2 = float('-inf')

            models.append([mse, coefficients])
            mse_list.append(mse)
            r2_list.append(r2)

        # calculate mean of MSE and R^2 over all folds
        mean_mse = np.mean(mse_list) if len(mse_list) > 0 else float('+inf')
        mean_r2 = np.mean(r2_list) if len(r2_list) > 0 else 0.0

        # update best model if this one has better metrics
        if mean_mse < best_mse:
            best_mse = mean_mse
            best_r2 = mean_r2
            # models = [[mse_1, coeffs_1], [mse_2, coeffs_2], [mse_3, coeffs_3], ...]
            best_model = sorted(models, key=lambda elem: elem[0])[0][1] if len(models) > 0 else None

    return best_model, best_r2


def compute_model_fitting(x_label, x_array, y_label, y_array):
    # number of folds for cross-validation
    fit_num_folds = 5
    # maximum degree of the polynomial to fit
    fit_degrees = 5
    # controls the randomness of each fold
    fit_seed = 4

    best_model_global = None
    best_r2_global = float('-inf')

    x_array = np.array(x_array)
    y_array = np.array(y_array)
    x_array, y_array = sort_arrays(x_array, y_array)
    
    if len(x_array) > 2:
        if len(x_array) < fit_num_folds:
            fit_num_folds = len(x_array)
        with warnings.catch_warnings():
            warnings.filterwarnings('error')
            try:
                best_model_global, best_r2_global = compute_k_flod_cv(x_array, y_array, fit_degrees, 
                                                                    fit_num_folds, fit_seed)
            except Warning:
                pass

    fitting_component_table.loc[len(fitting_component_table.index)] = [
        '(' + x_label + ', ' + y_label + ')',
        "{:.2f}".format(best_r2_global),
        equation_reduced_print(best_model_global) if best_model_global is not None else 'Insufficient data',
        equation_print(best_model_global) if best_model_global is not None else 'None'
    ]


###############################################################################
# APPLY MODELS
###############################################################################


try:
    cubeDataFrame = pd.read_csv(path + file + "_0.csv", encoding='cp1252')
except:
    cubeDataFrame = pd.read_csv(path + file + "_0.csv", encoding="utf-8")

cubeDataFrame = cubeDataFrame.rename(columns=str.lower)

# sort the attributes (measures excluded) in ascending order based on their cardinality
attributes = cubeDataFrame.drop(measures, axis=1).nunique().sort_values().index.tolist()
pivot_index = attributes[::2] #even
pivot_columns = attributes[1::2] #odd
pivot_table = cubeDataFrame.pivot(index=pivot_index, columns=pivot_columns, values=measure_to_explain)
pivot_table.to_json(path + "pivot_table.txt", orient='split')

# all the pairs for which to calculate the best fitting model
pairs_to_fit = compute_pairs_to_fit()
for pair in pairs_to_fit:
    x_data = cubeDataFrame[pair[0]].tolist()
    y_data = cubeDataFrame[pair[1]].tolist()
    points_df = pd.DataFrame({'x': x_data, 'y': y_data})
    # delete all the rows that in at least one of the two columns contain null as a value
    filtered_points_df = points_df[points_df['x'].notnull() & points_df['y'].notnull()]
    compute_model_fitting(pair[0], filtered_points_df['x'].tolist(), pair[1], filtered_points_df['y'].tolist())

fitting_component_table = fitting_component_table.sort_values(by=['Interest'], ascending=False, key=lambda x: x.astype(float))
fitting_component_table.to_csv(path + "test_fitting_component_table.csv", index=False)
fitting_component_table.to_json(path + "fitting_component_table.txt", orient='split', index=False)

cubeDataFrame.to_json(path + "cube.txt", orient="columns")

# sys.exit(1)