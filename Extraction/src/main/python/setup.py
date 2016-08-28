"""A setuptools based setup module.
See: https://packaging.python.org/en/latest/distributing.html
"""

from setuptools import setup, find_packages
from codecs import open
from os import path

here = path.abspath(path.dirname(__file__))

# Get the long description from the README file
with open(path.join(here, 'README.rst'), encoding='utf-8') as f:
    long_description = f.read()

setup(
    name='opensextant',
    version='1.1.1',

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

        # Specify the Python versions you support here. In particular, ensure
        # that you indicate whether you support Python 2, Python 3 or both.
        'Programming Language :: Python :: 2.7'
    ],

    # What does your project relate to?
    keywords='geography taxonomy tagging',

    packages=find_packages(exclude=['contrib', 'docs', 'tests']),

    install_requires=['pysolr>=3.2.0', 'chardet>=2.3.0']
)
