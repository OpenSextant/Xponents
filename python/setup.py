"""A setuptools based setup module.
See: https://packaging.python.org/en/latest/distributing.html
"""

from os import path

from setuptools import setup, find_packages

here = path.abspath(path.dirname(__file__))

# Get the long description from the README file
with open(path.join(here, 'README.rst'), encoding='utf-8') as f:
    long_description = f.read()

setup(
    name='opensextant',
    version='1.4.7',

    description='OpenSextant APIs and Utilities',
    long_description=long_description,

    # The project's main homepage.
    url='https://github.com/OpenSextant/Xponents',

    # Author details
    author='Marc Ubaldino',
    author_email='mubaldino@gmail.com',

    # Choose your license
    license='Apache Software License',

    # See https://pypi.python.org/pypi?%3Aaction=list_classifiers
    classifiers=[
        # How mature is this project? Common values are
        #   3 - Alpha
        #   4 - Beta
        #   5 - Production/Stable
        'Development Status :: 4 - Beta',

        # Indicate who your project is intended for
        'Intended Audience :: Developers',
        'Topic :: Text Processing',

        # Pick your license as you wish (should match "license" above)
        'License :: OSI Approved :: Apache Software License',

        # Python 3+ only.  Python 2 support remains in ver 1.1.x
        'Programming Language :: Python :: 3.6',
        'Programming Language :: Python :: 3.7',
        'Programming Language :: Python :: 3.8',
        'Programming Language :: Python :: 3.9',
        'Programming Language :: Python :: 3.10'
    ],

    # What does your project relate to?
    keywords='geography taxonomy tagging',

    packages=find_packages(exclude=['contrib', 'docs', 'tests']),
    package_data={'opensextant': ['./resources/geonames.org/*.txt', './resources/*.csv', './resources/*.cfg']},

    install_requires=['pysolr>=3.9.0', 'chardet>=3.0.0', 'requests', 'arrow>=1.1.0', 'PyGeodesy>=21.3.3']
)
